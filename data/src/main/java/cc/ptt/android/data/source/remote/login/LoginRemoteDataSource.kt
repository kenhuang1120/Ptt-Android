package cc.ptt.android.data.source.remote.login

import cc.ptt.android.data.model.remote.user.exist_user.ExistUser
import cc.ptt.android.data.model.remote.user.login.LoginEntity
import kotlinx.coroutines.flow.Flow

interface LoginRemoteDataSource {

    fun login(
        clientId: String,
        clientSecret: String,
        userName: String,
        password: String
    ): Flow<LoginEntity>

    fun existUser(
        clientId: String,
        clientSecret: String,
        userName: String
    ): Flow<ExistUser>
}
