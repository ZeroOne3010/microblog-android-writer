package com.example.microblogwriter.network

import com.example.microblogwriter.domain.Draft

class MicroblogApi {
    suspend fun publishPost(draft: Draft): Result<String> {
        // Wire up to Micro.blog endpoint in production.
        return Result.success(draft.postId ?: "remote-${draft.id.take(8)}")
    }

    suspend fun uploadImage(localUri: String, alt: String): Result<String> {
        return Result.success("https://micro.blog/uploads/${localUri.hashCode()}?alt=${alt.hashCode()}")
    }

    suspend fun fetchRecentPosts(): Result<List<Draft>> = Result.success(emptyList())
}
