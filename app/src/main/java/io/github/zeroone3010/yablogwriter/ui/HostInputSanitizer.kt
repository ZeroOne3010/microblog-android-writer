package io.github.zeroone3010.yablogwriter.ui

private val whitespaceRegex = Regex("\\s+")

fun sanitizeHostInput(input: String): String = input.replace(whitespaceRegex, "")
