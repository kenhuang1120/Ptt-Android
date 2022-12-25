package cc.ptt.android

import cc.ptt.android.common.apihelper.ApiHelper

class TestApiHelperImpl : ApiHelper {

    override fun getHost(): String {
        return BuildConfig.api_host
    }

    override fun getClientId(): String {
        return ApiHelper.CLIENT_ID
    }

    override fun getClientSecret(): String {
        return ApiHelper.CLIENT_SECRET
    }
}
