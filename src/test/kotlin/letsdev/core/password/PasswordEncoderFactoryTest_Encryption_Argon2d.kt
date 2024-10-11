package letsdev.core.password

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.*
import io.kotest.matchers.equals.*
import io.kotest.matchers.string.*
import io.mockk.every
import io.mockk.mockk
import letsdev.core.password.encoder.GeneralPasswordEncoderType.Argon2Variant
import letsdev.core.password.encoder.option.Argon2dPasswordEncoderOption
import letsdev.core.password.exception.PasswordEncoderEncryptionException

class PasswordEncoderFactoryTest_Encryption_Argon2d: FreeSpec({
    lateinit var argon2dOption: Argon2dPasswordEncoderOption
    lateinit var factory: PasswordEncoderFactory
    lateinit var customSalt: ByteArray
    val argon2Prefix = "{argon2}"

    beforeTest {
        argon2dOption = mockk<Argon2dPasswordEncoderOption> {
            every { encoderType() } returns Argon2Variant.ARGON2D
            every { `as`(Argon2dPasswordEncoderOption::class.java) } returns this
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
        customSalt = byteArrayOf(
                0x1F, 0x2A, 0x3B, 0x4C, 0x5D, 0x6E, 0x7F, 0x7F,
                0x9B.toByte(), 0xAC.toByte(), 0xBD.toByte(), 0xCE.toByte(), 0xDF.toByte(), 0xEA.toByte(), 0x3B, 0x0C
        )
    }

    ">>> argon2d(format): 팩토리에서 생성된 argon2d 인코더에서" - {
        val encoder = factory.create(argon2dOption)
        val rawPassword = "abcd1234"

        ">>> encode 메서드를 사용하면" - {
            val encodedPassword = encoder.encode(rawPassword)

            "{argon2}로 시작하는 문자열을 생성한다." {
                encodedPassword shouldStartWith argon2Prefix
            }

            "파라미터가 올바른 양식으로 입력된다." {
                encodedPassword shouldStartWith "$argon2Prefix\$argon2d\$v=19\$m=49343,t=1,p=1"
            }
        }
    }

    ">>> argon2d(encode, matches): 팩토리에서 만든 Argon2id 인코더로" - {
        val encoder = factory.create(argon2dOption)
        val rawPassword = "abcd1234"

        ">>> 암호화하면" - {
            val encodedPassword = encoder.encode(rawPassword)

            "동일한 평문 비밀번호와 비교하여 일치한다." {
                encoder.matches(rawPassword, encodedPassword)
                        .shouldBeTrue()
            }
            "다른 평문 비밀번호와 비교하여 일치하지 않는다." {
                val differentPassword = "wrongPassword"
                encoder.matches(differentPassword, encodedPassword)
                        .shouldNotBeTrue()
            }
        }
    }

    ">>> argon2d(encode + custom salt): 길이가 16인 솔트로 인코딩한 문자열은 {argon2}로 시작하며 평문 비교가 가능하다." - {
        val encoder = factory.createCustomSaltingEncoder(argon2dOption)
        val rawPassword = "abcd1234"

        ">>> 길이가 16인 솔트로 인코딩한 문자열이" - {
            val encodedPassword = encoder.encodeWithCustomSalt(rawPassword, customSalt)

            "{argon2}로 시작한다." {
                encodedPassword.startsWith(argon2Prefix)
                        .shouldBeTrue()
            }
            "동일한 평문 비밀번호와 비교하여 일치한다." {
                encoder.matches(rawPassword, encodedPassword)
                        .shouldBeTrue()
            }
        }

        "길이가 16이 아닌 솔트로 인코딩 할 수 없다." {
            val wrongSalt = ByteArray(argon2dOption.saltLength + 1) { 0x1F }

            shouldThrow<PasswordEncoderEncryptionException> {
                encoder.encodeWithCustomSalt(rawPassword, wrongSalt)
            }
        }
    }

    "argon2d(custom salt): 같은 암호를 같은 솔트로 인코딩한 문자열은 서로 일치한다." {
        val encoder = factory.createCustomSaltingEncoder(argon2dOption)
        val rawPassword = "abcd1234"

        val encodedPasswordA = encoder.encodeWithCustomSalt(rawPassword, customSalt)
        val encodedPasswordB = encoder.encodeWithCustomSalt(rawPassword, customSalt)

        encodedPasswordA shouldBeEqual encodedPasswordB
    }
})