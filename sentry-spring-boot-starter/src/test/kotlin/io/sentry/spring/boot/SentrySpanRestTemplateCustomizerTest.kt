package io.sentry.spring.boot

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import java.io.IOException
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.RestTemplate

class SentrySpanRestTemplateCustomizerTest {
    class Fixture {
        val sentryOptions = SentryOptions()
        val hub = mock<IHub>()
        val restTemplate = RestTemplate()
        var mockServer = MockRestServiceServer.createServer(restTemplate)
        val transaction = SentryTracer(TransactionContext("aTransaction", "op", true), hub)
        internal val customizer = SentrySpanRestTemplateCustomizer(hub)

        init {
            whenever(hub.options).thenReturn(sentryOptions)
        }

        fun getSut(isTransactionActive: Boolean, status: HttpStatus = HttpStatus.OK, throwIOException: Boolean = false): RestTemplate {
            customizer.customize(restTemplate)

            if (isTransactionActive) {
                val scope = Scope(sentryOptions)
                scope.setTransaction(transaction)
                whenever(hub.span).thenReturn(transaction)

                val scenario = mockServer.expect(MockRestRequestMatchers.requestTo("/test/123"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                    .andExpect {
                        // must have trace id from the parent transaction and must not contain spanId from the parent transaction
                        assertThat(it.headers["sentry-trace"]!!.first()).startsWith(transaction.spanContext.traceId.toString())
                            .endsWith("-1")
                            .doesNotContain(transaction.spanContext.spanId.toString())
                    }
                if (throwIOException) {
                    scenario.andRespond {
                        throw IOException()
                    }
                } else {
                    scenario.andRespond(MockRestResponseCreators.withStatus(status).body("OK").contentType(MediaType.APPLICATION_JSON))
                }
            } else {
                mockServer.expect(MockRestRequestMatchers.requestTo("/test/123"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                    .andRespond(MockRestResponseCreators.withStatus(status).body("OK").contentType(MediaType.APPLICATION_JSON))
            }

            return restTemplate
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when transaction is active, creates span around RestTemplate HTTP call`() {
        val result = fixture.getSut(isTransactionActive = true).getForObject("/test/{id}", String::class.java, 123)

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET /test/123")
        assertThat(span.status).isEqualTo(SpanStatus.OK)
        fixture.mockServer.verify()
    }

    @Test
    fun `when transaction is active and response code is not 2xx, creates span with error status around RestTemplate HTTP call`() {
        try {
            fixture.getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR).getForObject("/test/{id}", String::class.java, 123)
        } catch (e: Throwable) {
        }
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET /test/123")
        assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
        fixture.mockServer.verify()
    }

    @Test
    fun `when transaction is active and throws IO exception, creates span with error status around RestTemplate HTTP call`() {
        try {
            fixture.getSut(isTransactionActive = true, throwIOException = true).getForObject("/test/{id}", String::class.java, 123)
        } catch (e: Throwable) {
        }
        assertThat(fixture.transaction.spans).hasSize(1)
        val span = fixture.transaction.spans.first()
        assertThat(span.operation).isEqualTo("http.client")
        assertThat(span.description).isEqualTo("GET /test/123")
        assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
        fixture.mockServer.verify()
    }

    @Test
    fun `when transaction is not active, does not create span around RestTemplate HTTP call`() {
        val result = fixture.getSut(isTransactionActive = false).getForObject("/test/{id}", String::class.java, 123)

        assertThat(result).isEqualTo("OK")
        assertThat(fixture.transaction.spans).isEmpty()
        fixture.mockServer.verify()
    }

    @Test
    fun `avoids duplicate registration`() {
        val restTemplate = fixture.getSut(isTransactionActive = true)

        fixture.customizer.customize(restTemplate)
        assertThat(restTemplate.interceptors).hasSize(1)
        fixture.customizer.customize(restTemplate)
        assertThat(restTemplate.interceptors).hasSize(1)
    }
}
