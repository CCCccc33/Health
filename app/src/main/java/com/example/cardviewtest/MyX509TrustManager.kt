package com.example.cardviewtest

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class MyX509TrustManager : X509TrustManager {
    /**
     * 校验客户端证书（此处不做校验，空实现）
     */
    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        // 空实现，不校验客户端证书
    }

    /**
     * 校验服务端证书（此处不做校验，空实现）
     */
    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // 空实现，不校验服务端证书
    }

    /**
     * 返回受信任的证书数组
     */
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        // 修复原 Java 代码返回 null 的问题，返回空数组避免空指针异常
        return emptyArray()
    }
}