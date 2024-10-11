package letsdev.core.password.property

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.*
import io.kotest.core.spec.IsolationMode
import letsdev.core.password.property.argon2.PasswordEncoderArgon2Properties
import letsdev.core.password.property.bcrypt.PasswordEncoderBcryptProperties
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import kotlin.math.ceil

class PasswordEncoderPropertiesTest_Bind : StringSpec({

    isolationMode = IsolationMode.InstancePerTest // 테스트마다 새 인스턴스 생성

    "속성을 생략하면 -> 기본값이 올바르게 바인딩 된다." {
        val source = MapConfigurationPropertySource(mapOf<String, Any>())
        val binder = Binder(source)

        val properties = binder
                .bind("letsdev.password", Bindable.of(PasswordEncoderProperties::class.java))
                .orElseGet { PasswordEncoderProperties(null, null, null) }

        properties.defaultEncoder() shouldBe PasswordEncoderProperties.SupportedPasswordEncoderType.BCRYPT
        properties.bcrypt() shouldBe PasswordEncoderBcryptProperties.defaultInstance()
        properties.argon2() shouldBe PasswordEncoderArgon2Properties.defaultInstance()
    }

    "속성을 입력하면 -> 입력된 값이 올바르게 바인딩 된다." {
        val source = MapConfigurationPropertySource(mapOf(
            "letsdev.password.default-encoder" to "argon2",
            "letsdev.password.bcrypt.strength" to "12",
            "letsdev.password.argon2.memory-input" to "4096",
            "letsdev.password.argon2.memory-gain" to "2.0",
        ))
        val binder = Binder(source)

        val properties = binder
                .bind("letsdev.password", Bindable.of(PasswordEncoderProperties::class.java))
                .get()

        properties.defaultEncoder() shouldBe PasswordEncoderProperties.SupportedPasswordEncoderType.ARGON2
        properties.bcrypt.strength() shouldBe 12
        properties.argon2.memoryInput() shouldBe 4096
        properties.argon2.memoryGain() shouldBe 2.0
        properties.argon2.memory() shouldBe ceil(4096 * 2.0).toInt()
    }
})
