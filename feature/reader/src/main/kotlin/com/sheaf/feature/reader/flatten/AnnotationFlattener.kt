package com.sheaf.feature.reader.flatten

import com.sheaf.core.domain.model.Annotation

/** Burns Sheaf's overlay annotations (ink, highlight, notes, signature) into the PDF itself. */
interface AnnotationFlattener {
    /** Writes a flattened copy of [uri] containing [annotationsByPage]; returns the file path or null. */
    suspend fun flatten(uri: String, annotationsByPage: Map<Int, List<Annotation>>): String?
}
