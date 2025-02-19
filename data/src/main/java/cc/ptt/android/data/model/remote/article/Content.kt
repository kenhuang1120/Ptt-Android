package cc.ptt.android.data.model.remote.article

import com.google.gson.annotations.SerializedName

data class Content(
    @SerializedName("color0")
    val color0: Color0,
    @SerializedName("color1")
    val color1: Color1,
    @SerializedName("text")
    val text: String
)
