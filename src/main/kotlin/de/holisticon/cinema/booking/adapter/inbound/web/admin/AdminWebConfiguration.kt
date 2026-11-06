package de.holisticon.cinema.booking.adapter.inbound.web.admin

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class AdminWebConfiguration(@Value("\${cinema.admin.token:}") token: String) : WebMvcConfigurer {
    private val authentication = AdminTokenInterceptor(token)

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authentication).addPathPatterns("/admin/**")
    }
}

private class AdminTokenInterceptor(token: String) : HandlerInterceptor {
    private val tokenDigest = token.takeIf { it.isNotBlank() }?.let(::digest)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
        val bearer = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring("Bearer ".length)
        if (tokenDigest != null && bearer != null && MessageDigest.isEqual(tokenDigest, digest(bearer))) {
            return true
        }

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        return false
    }

    private fun digest(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
