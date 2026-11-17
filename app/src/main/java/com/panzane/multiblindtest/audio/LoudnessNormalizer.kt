package com.panzane.multiblindtest.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan
import kotlin.math.tanh

/**
 * Ramène chaque extrait au même volume perçu : les extraits Deezer ne sont pas masterisés au même niveau,
 * ce qui s'entend surtout quand il n'en reste que deux.
 *
 * Mesure la sonie intégrée (LUFS, norme ITU-R BS.1770 : pondération K et blocs de 400 ms, sans le seuil relatif)
 * pendant la lecture, puis applique le gain qui l'amène à [TARGET_LUFS]. La mesure survit aux retours au début
 * (flush) : l'extrait tournant en boucle, elle est stable après un premier passage.
 */
@OptIn(UnstableApi::class)
class LoudnessNormalizer : BaseAudioProcessor() {

    private var channels = 0
    private var sampleRate = 0
    private var isFloat = false

    // Filtres de pondération K (deux biquads), un état par canal
    private var shelf = Biquad.IDENTITY
    private var highPass = Biquad.IDENTITY
    private var shelfState = DoubleArray(0)
    private var highPassState = DoubleArray(0)

    // Bloc de 400 ms en cours
    private var blockFrames = 0
    private var blockFrameCount = 0
    private var blockSum = 0.0

    // Blocs retenus (au-dessus du seuil de silence) ; plafonné, l'extrait bouclant sur lui-même
    private var gatedPowerSum = 0.0
    private var gatedBlockCount = 0

    private var gain = 1.0
    private var targetGain = 1.0
    private var smoothing = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        val format = inputAudioFormat
        if (format.sampleRate == sampleRate && format.channelCount == channels) return
        // Nouveau format : on repart de zéro (la mesure dépend de la fréquence d'échantillonnage)
        channels = format.channelCount
        sampleRate = format.sampleRate
        isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        shelf = Biquad.kWeightingShelf(sampleRate.toDouble())
        highPass = Biquad.kWeightingHighPass(sampleRate.toDouble())
        shelfState = DoubleArray(channels * 2)
        highPassState = DoubleArray(channels * 2)
        blockFrames = sampleRate * BLOCK_MS / 1000
        smoothing = 1 - exp(-1.0 / (sampleRate * SMOOTHING_SECONDS))
        resetMeasure()
    }

    override fun onReset() {
        channels = 0
        sampleRate = 0
        resetMeasure()
    }

    private fun resetMeasure() {
        blockFrameCount = 0
        blockSum = 0.0
        gatedPowerSum = 0.0
        gatedBlockCount = 0
        gain = 1.0
        targetGain = 1.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerSample = if (isFloat) 4 else 2
        val frameCount = inputBuffer.remaining() / (bytesPerSample * channels)
        if (frameCount == 0) return
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(frameCount * bytesPerSample * channels).order(ByteOrder.nativeOrder())

        repeat(frameCount) {
            var framePower = 0.0
            gain += (targetGain - gain) * smoothing
            for (ch in 0 until channels) {
                val x = if (isFloat) input.getFloat().toDouble() else input.getShort() / 32768.0
                val weighted = highPass.process(shelf.process(x, shelfState, ch * 2), highPassState, ch * 2)
                framePower += weighted * weighted
                val y = softClip(x * gain)
                if (isFloat) output.putFloat(y.toFloat()) else output.putShort((y * 32767).toInt().toShort())
            }
            blockSum += framePower
            if (++blockFrameCount == blockFrames) closeBlock()
        }
        output.flip()
    }

    private fun closeBlock() {
        val power = blockSum / blockFrameCount
        blockSum = 0.0
        blockFrameCount = 0
        if (lufs(power) < SILENCE_GATE_LUFS || gatedBlockCount >= MAX_BLOCKS) return
        gatedPowerSum += power
        gatedBlockCount++
        val loudness = lufs(gatedPowerSum / gatedBlockCount)
        val gainDb = (TARGET_LUFS - loudness).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        targetGain = 10.0.pow(gainDb / 20)
    }

    /** Écrêtage doux au-delà de [CLIP_KNEE], pour que les extraits remontés ne saturent pas. */
    private fun softClip(x: Double): Double {
        val abs = kotlin.math.abs(x)
        if (abs <= CLIP_KNEE) return x
        val headroom = 1 - CLIP_KNEE
        return kotlin.math.sign(x) * (CLIP_KNEE + headroom * tanh((abs - CLIP_KNEE) / headroom))
    }

    private fun lufs(power: Double): Double = -0.691 + 10 * log10(power.coerceAtLeast(1e-12))

    /** Filtre biquad (forme directe II transposée) ; l'état de chaque canal est rangé dans un tableau partagé. */
    private class Biquad(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double,
    ) {
        fun process(x: Double, state: DoubleArray, offset: Int): Double {
            val y = b0 * x + state[offset]
            state[offset] = b1 * x - a1 * y + state[offset + 1]
            state[offset + 1] = b2 * x - a2 * y
            return y
        }

        companion object {
            val IDENTITY = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

            /** Étage 1 de la pondération K : plateau haut d'environ +4 dB (coefficients recalculés pour [fs]). */
            fun kWeightingShelf(fs: Double): Biquad {
                val k = tan(PI * 1681.974450955533 / fs)
                val q = 0.7071752369554196
                val vh = 10.0.pow(3.999843853973347 / 20)
                val vb = vh.pow(0.4996667741545416)
                val a0 = 1 + k / q + k * k
                return Biquad(
                    (vh + vb * k / q + k * k) / a0,
                    2 * (k * k - vh) / a0,
                    (vh - vb * k / q + k * k) / a0,
                    2 * (k * k - 1) / a0,
                    (1 - k / q + k * k) / a0,
                )
            }

            /** Étage 2 de la pondération K : passe-haut vers 38 Hz. */
            fun kWeightingHighPass(fs: Double): Biquad {
                val k = tan(PI * 38.13547087602444 / fs)
                val q = 0.5003270373238773
                val a0 = 1 + k / q + k * k
                return Biquad(1.0, -2.0, 1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0)
            }
        }
    }

    private companion object {
        /** Niveau visé : celui des plateformes de streaming, sous la plupart des extraits (souvent masterisés plus fort). */
        const val TARGET_LUFS = -14.0
        const val MIN_GAIN_DB = -15.0
        const val MAX_GAIN_DB = 9.0
        const val SILENCE_GATE_LUFS = -70.0
        const val BLOCK_MS = 400
        /** 5 min de son retenu : bien plus qu'un extrait de 30 s, dont la mesure n'évolue plus ensuite. */
        const val MAX_BLOCKS = 750
        /** Temps de réponse du gain : assez lent pour ne pas « pomper », assez rapide pour s'ajuster en quelques secondes. */
        const val SMOOTHING_SECONDS = 1.5
        const val CLIP_KNEE = 0.9
    }
}
