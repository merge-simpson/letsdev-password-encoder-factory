package letsdev.core.password

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.*
import io.kotest.matchers.types.*
import io.mockk.every
import io.mockk.mockk
import letsdev.core.password.encoder.GeneralPasswordEncoderType
import letsdev.core.password.encoder.GeneralPasswordEncoderType.Argon2Variant
import letsdev.core.password.encoder.option.Argon2idPasswordEncoderOption
import letsdev.core.password.encoder.option.BcryptPasswordEncoderOption
import java.util.concurrent.TimeUnit
// import kotlin.test.*

class PasswordEncoderFactoryTest_Cache: StringSpec({

    lateinit var bcryptOption: BcryptPasswordEncoderOption
    lateinit var argon2IdOption: Argon2idPasswordEncoderOption
    lateinit var factory: PasswordEncoderFactory
    lateinit var factoryWithShortTtl: PasswordEncoderFactory
    lateinit var factoryWithSmallSize: PasswordEncoderFactory

    beforeTest {
        bcryptOption = mockk<BcryptPasswordEncoderOption> {
            every { encoderType() } returns GeneralPasswordEncoderType.BCRYPT
            every { `as`(BcryptPasswordEncoderOption::class.java) } returns this
            every { strength } returns 12
        }
        argon2IdOption = mockk<Argon2idPasswordEncoderOption> {
            every { encoderType() } returns Argon2Variant.ARGON2ID
            every { `as`(Argon2idPasswordEncoderOption::class.java) } returns this
            every { saltLength } returns 16
            every { hashLength } returns 32
            every { parallelism } returns 1
            every { memory } returns 49343
            every { iterations } returns 1
            every { alpha } returns 0.95f
            every { gain } returns 1.0f
            every { memoryInput } returns 93750
        }

        factory = PasswordEncoderFactory()
        factoryWithShortTtl = PasswordEncoderFactory.builder()
                .expireAfterAccess(1, TimeUnit.NANOSECONDS)
                .build()
        factoryWithSmallSize = PasswordEncoderFactory.builder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(1)
                .removalListener { key, value, cause ->
                    println("[Cache entry for key $key was removed because of: $cause] Removed value: $value")
                }
                .build()
    }

    (
            "기본 팩토리 캐싱: 동일한 옵션 객체를 제공하면 -> " +
            "캐싱된 인코더 객체를 가져온다."
    ) {
        val encoder1 = factory.create(bcryptOption)
        val encoder2 = factory.create(bcryptOption)

        encoder1 shouldBeSameInstanceAs encoder2
    }

    (
            "캐시 Key 동일성: 참조가 다르고 내용이 같은 옵션 객체를 제공하면 -> " +
            "캐싱된 인코더 객체를 가져온다."
    ) {
        // 같은 내용을 갖는 서로 다른 옵션 생성
        val newBcryptOption1 = BcryptPasswordEncoderOption(bcryptOption.strength)
        val newBcryptOption2 = BcryptPasswordEncoderOption(bcryptOption.strength)

        val encoder1 = factory.create(newBcryptOption1)
        val encoder2 = factory.create(newBcryptOption2)

        encoder1 shouldBeSameInstanceAs encoder2
    }

    (
            "캐시 Key 동일성: 동일한 옵션 객체를 제공하면 -> " +
             "PasswordEncoder 객체와 CustomSaltingPasswordEncoder 객체는 캐싱이 호환된다."
    ) {
        val bcryptEncoder = factoryWithSmallSize.create(bcryptOption)
        val customSaltingBcryptEncoder =
                factoryWithSmallSize.createCustomSaltingEncoder(bcryptOption)

        bcryptEncoder shouldBeSameInstanceAs customSaltingBcryptEncoder
    }

    (
            "캐시 Key 동일성: 참조가 다르고 내용이 같은 옵션 객체를 제공하면 -> " +
            "PasswordEncoder, CustomSaltingPasswordEncoder 캐싱이 호환된다."
    ) {
        val newBcryptOption1 = BcryptPasswordEncoderOption(bcryptOption.strength)
        val newBcryptOption2 = BcryptPasswordEncoderOption(bcryptOption.strength)

        val bcryptEncoder = factoryWithSmallSize.create(newBcryptOption1)
        val customSaltingBcryptEncoder =
                factoryWithSmallSize.createCustomSaltingEncoder(newBcryptOption2)

        bcryptEncoder shouldBeSameInstanceAs customSaltingBcryptEncoder
    }

    "팩토리 빌더(시간): 입력한 시간 동안 캐싱이 된다." {
        val encoder1 = factoryWithSmallSize.create(bcryptOption)
        Thread.sleep(1)
        val encoder2 = factoryWithSmallSize.create(bcryptOption)

        // 객체가 캐싱되었는지
        encoder1 shouldBeSameInstanceAs encoder2
    }

    "시간(만료): 시간이 만료되면 -> 캐싱되지 않은 새 객체가 생성된다." {
        // 짧은 수명을 가지는 팩토리 사용
        val encoder1 = factoryWithShortTtl.create(bcryptOption)
        Thread.sleep(1)
        val encoder2 = factoryWithShortTtl.create(bcryptOption)

        // 객체가 다르게 생성되었는지 검증
        encoder1 shouldNotBeSameInstanceAs encoder2
    }

    "사이즈: 사이즈를 1인 팩토리를 사용해도 -> 하나는 캐싱이 된다." {
        val bcryptEncoder1 = factoryWithSmallSize.create(bcryptOption)
        val bcryptEncoder2 = factoryWithSmallSize.create(bcryptOption)

        bcryptEncoder1 shouldBeSameInstanceAs bcryptEncoder2
    }

    "사이즈(만료): 팩토리 캐싱 사이즈를 초과하면 -> 오래된 객체가 삭제된다." {
        val bcryptEncoder1 = factoryWithSmallSize.create(bcryptOption)
        Thread.sleep(1)
        val argon2Encoder1 = factoryWithSmallSize.create(argon2IdOption)
        Thread.sleep(1)
        val argon2Encoder2 = factoryWithSmallSize.create(argon2IdOption) // get cached instance
        Thread.sleep(1)
        val bcryptEncoder2 = factoryWithSmallSize.create(bcryptOption) // get new instance

        // 하나만 캐싱되므로 두 조건은 XOR 관계(!=)여야 함. (기존 걸 만료시키는지, 나중 걸 저장하지 않는지 모를 때)
        (bcryptEncoder1 == bcryptEncoder2) shouldNotBe (argon2Encoder1 == argon2Encoder2)
        bcryptEncoder1 shouldNotBeSameInstanceAs bcryptEncoder2
        argon2Encoder1 shouldBeSameInstanceAs argon2Encoder2
    }
})