package com.homecontrol.core.security.di

import com.homecontrol.core.security.KeystoreCipher
import org.koin.dsl.module

val securityModule = module {
    single { KeystoreCipher() }
}
