package com.ptd450vp.labelprinter.printer

import com.ptd450vp.labelprinter.model.TapeWidth

/**
 * Parsed 32-byte status packet returned by Brother P-touch printers in response to `ESC i S` (1B 69 53).
 */
data class PrinterStatus(
    val rawBytes: ByteArray,
    val modelCode: Int,
    val error1: Int,
    val error2: Int,
    val mediaWidthByte: Int,
    val mediaTypeByte: Int,
    val statusTypeByte: Int,
    val phaseTypeByte: Int,
    val tapeColorByte: Int,
    val textColorByte: Int
) {
    val tapeWidth: TapeWidth?
        get() = TapeWidth.fromMediaWidthByte(mediaWidthByte)

    val hasError: Boolean
        get() = error1 != 0 || error2 != 0

    val errorMessages: List<String>
        get() = buildList {
            if (error1 and 0x01 != 0) add("Нет кассеты с лентой")
            if (error1 and 0x02 != 0) add("Лента закончилась")
            if (error1 and 0x04 != 0) add("Замятие резака")
            if (error1 and 0x08 != 0) add("Слабый заряд батарей")
            if (error1 and 0x10 != 0) add("Принтер занят")
            if (error1 and 0x40 != 0) add("Неверный адаптер питания (высокое напряжение)")
            if (error1 and 0x80 != 0) add("Ошибка охлаждения / вентилятора")
            if (error2 and 0x01 != 0) add("Замените кассету")
            if (error2 and 0x02 != 0) add("Буфер расширения заполнен")
            if (error2 and 0x04 != 0) add("Ошибка передачи данных")
            if (error2 and 0x08 != 0) add("Буфер передачи заполнен")
            if (error2 and 0x10 != 0) add("Открыта крышка кассеты")
            if (error2 and 0x20 != 0) add("Перегрев печатающей головки")
            if (error2 and 0x40 != 0) add("Нет ленты (несовместимая кассета)")
        }

    val modelName: String
        get() = when (modelCode) {
            0x69 -> "PT-D450 / PT-D450VP"
            0x67 -> "PT-P700"
            0x68 -> "PT-P750W"
            0x6B -> "PT-D600"
            0x6E -> "PT-P710BT"
            else -> "Brother (0x${modelCode.toString(16).uppercase()})"
        }

    val mediaTypeName: String
        get() = when (mediaTypeByte) {
            0x00 -> "Нет ленты"
            0x01 -> "Ламинированная TZe"
            0x03 -> "Неламинированная"
            0x11 -> "Термоусадочная трубка (HSe)"
            0xFF -> "Несовместимая кассета"
            else -> "Тип 0x${mediaTypeByte.toString(16).uppercase()}"
        }

    val tapeColorName: String
        get() = when (tapeColorByte) {
            0x01 -> "Белая"
            0x02 -> "Другая"
            0x03 -> "Прозрачная"
            0x04 -> "Красная"
            0x05 -> "Синяя"
            0x06 -> "Жёлтая"
            0x07 -> "Зелёная"
            0x08 -> "Чёрная"
            0x09 -> "Прозрачная (белый текст)"
            0x20 -> "Матовая белая"
            0x21 -> "Матовая прозрачная"
            0x22 -> "Матовая серебряная"
            0x23 -> "Сатин золотой"
            0x24 -> "Сатин серебряный"
            0x30 -> "Синий металлик"
            0x31 -> "Красный металлик"
            0x40 -> "Флуоресцентная оранжевая"
            0x41 -> "Флуоресцентная жёлтая"
            0x62 -> "Чёрная (золотой текст)"
            0xF0 -> "Очищающая"
            0xF1 -> "Трафаретная"
            0xFF -> "Несовместимая"
            else -> "Код 0x${tapeColorByte.toString(16).uppercase()}"
        }

    val textColorName: String
        get() = when (textColorByte) {
            0x01 -> "Белый"
            0x04 -> "Красный"
            0x05 -> "Синий"
            0x08 -> "Чёрный"
            0x0A -> "Золотой"
            0x62 -> "Синий (F)"
            0xF0 -> "Очищающая"
            0xF1 -> "Трафаретная"
            0x02 -> "Другой"
            else -> "Код 0x${textColorByte.toString(16).uppercase()}"
        }

    val statusTypeName: String
        get() = when (statusTypeByte) {
            0x00 -> "Ответ на запрос статуса"
            0x01 -> "Печать завершена"
            0x02 -> "Ошибка"
            0x04 -> "Выключен"
            0x05 -> "Уведомление"
            0x06 -> "Смена фазы"
            else -> "Статус 0x${statusTypeByte.toString(16).uppercase()}"
        }

    fun toHexString(): String =
        rawBytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrinterStatus) return false
        return rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int = rawBytes.contentHashCode()

    companion object {
        const val PACKET_SIZE = 32
        const val HEADER_MARK = 0x80
        const val HEADER_SIZE = 0x20

        fun parse(bytes: ByteArray): PrinterStatus? {
            if (bytes.size < PACKET_SIZE) return null
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 != HEADER_MARK || b1 != HEADER_SIZE) return null
            val slice = bytes.copyOf(PACKET_SIZE)
            return PrinterStatus(
                rawBytes = slice,
                modelCode = slice[4].toInt() and 0xFF,
                error1 = slice[8].toInt() and 0xFF,
                error2 = slice[9].toInt() and 0xFF,
                mediaWidthByte = slice[10].toInt() and 0xFF,
                mediaTypeByte = slice[11].toInt() and 0xFF,
                statusTypeByte = slice[18].toInt() and 0xFF,
                phaseTypeByte = slice[19].toInt() and 0xFF,
                tapeColorByte = slice[24].toInt() and 0xFF,
                textColorByte = slice[25].toInt() and 0xFF
            )
        }

        /** Creates a realistic synthetic 32-byte status packet for the mock simulator and tests. */
        fun synthetic(
            tapeWidth: TapeWidth = TapeWidth.MM_12,
            mediaTypeByte: Int = 0x01,
            tapeColorByte: Int = 0x01,
            textColorByte: Int = 0x08,
            error1: Int = 0x00,
            error2: Int = 0x00,
            statusTypeByte: Int = 0x00
        ): PrinterStatus {
            val bytes = ByteArray(PACKET_SIZE)
            bytes[0] = HEADER_MARK.toByte()
            bytes[1] = HEADER_SIZE.toByte()
            bytes[2] = 0x42 // 'B'
            bytes[3] = 0x30 // '0'
            bytes[4] = 0x69 // PT-D450 / PT-D450VP model identifier (verified on real hardware)
            bytes[5] = 0x30
            bytes[8] = error1.toByte()
            bytes[9] = error2.toByte()
            bytes[10] = tapeWidth.mediaWidthByte.toByte()
            bytes[11] = mediaTypeByte.toByte()
            bytes[18] = statusTypeByte.toByte()
            bytes[24] = tapeColorByte.toByte()
            bytes[25] = textColorByte.toByte()
            return parse(bytes)!!
        }
    }
}
