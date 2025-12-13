
package com.example.dermascanai

data class BlogPost(
    var postId: String = "",
    var userId: String = "",
    var fullName: String = "",
    var profilePicBase64: String = "",
    var content: String = "",
    val imageUrl: String? = null,         // replace postImageBase64
    var timestamp: Long = 0L,
    var likeCount: Int = 0,
    var commentCount: Int = 0,
    var likes: MutableMap<String, Boolean> = mutableMapOf(),
    var clinicName: String = "",
    var clinicAddress: String = "",
    var clinicContact: String = ""
)

data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileImageBase64: String? = null,
    val comment: String = "",
    val timestamp: Long = 0L,
    val parentCommentId: String? = null,
    var replies: MutableMap<String, Comment> = mutableMapOf()
) {
    @Transient
    var repliesList: List<Comment> = listOf()
}
