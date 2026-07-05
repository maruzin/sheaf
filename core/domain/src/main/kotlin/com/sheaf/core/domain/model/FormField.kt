package com.sheaf.core.domain.model

/** An interactive AcroForm field with its on-page rectangle in page-normalized coords (top-left). */
data class FormField(
    val name: String,
    val pageIndex: Int,
    val type: FormFieldType,
    val rect: NormRect,
    val value: String,
    val options: List<String> = emptyList(),
)

enum class FormFieldType { Text, Checkbox, Choice, Unsupported }

/** Rectangle in page-normalized space (0..1), origin top-left. */
data class NormRect(val x: Float, val y: Float, val w: Float, val h: Float)
