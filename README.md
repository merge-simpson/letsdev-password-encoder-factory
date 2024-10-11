# About API Module

## Bundled

- letsdev-password-encoder-api  
  `com.github.merge-simpson:letsdev-password-encoder-api:0.1.1`
  - letsdev-password-encoder-port  
    `com.github.merge-simpson:letsdev-password-encoder-port:0.1.1`
  - letsdev-password-encoder-exception  
    `com.github.merge-simpson:letsdev-password-encoder-exception:0.1.1`

## Installation

`build.gradle.kts`에서 다음과 같이 추가합니다.
(또는 번들된 의존성을 각각 추가할 수 있습니다.)

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") } // added
}

dependencies {
    implementation("com.github.merge-simpson:letsdev-password-encoder-factory:0.1.1") // added
}
```

# Features

이 모듈은 Spring Crypto 라이브러리에 의존성을 갖고 있습니다. (`org.springframework.security:spring-security-crypto`)

- [Password Encoder Factory](#password-encoder-factory)
- [Cache Password Encoder Instances And Expire Them Automatically.](#패스워드-인코더-객체-캐싱)

# Password Encoder Factory

제공하는 패스워드 인코더 목록은 다음과 같습니다.

- bcrypt
- argon2
    - argon2id
    - argon2d

## 패스워드 인코더 인스턴스 생성하기

팩토리는 다음처럼 두 방식으로 패스워드 인코더를 생성합니다.

```java
// 일반 패스워드 인코더 (내부에서 솔트를 자동으로 생성합니다.)
var encoder = factory.create(option);

// 커스텀 솔트를 사용하는 패스워드 인코더
//  직접 만든 솔트를 넣을 수 있으며, 내부에서 추가적인 랜덤 솔트를 만들지 않습니다.
var customSaltingEncoder = factory.createCustomSaltingEncoder(option);
```

- option: &lt;&lt;interface&gt;&gt; PasswordEncoderOption
- option 인스턴스는 `PasswordEncoderType` 객체를 반환하는 `encoderType()` 메서드를 갖습니다.

Examples

- [Create bcrypt password encoder](#bcrypt)
- [Create argon2id password encoder](#argon2id)
- [Create argon2d password encoder](#argon2d)

### BCrypt

```java
var factory = new PasswordEncoderFactory();
var option = new BcryptPasswordEncoderOption(12);

var bcryptPasswordEncoder = factory.create(option);
```

### Argon2id

```java
var factory = new PasswordEncoderFactory();
var option = Argon2idPasswordEncoderOption.fromDefaultBuilder()
        .gain(3f) // optional
        .build();

var bcryptPasswordEncoder = factory.create(option);
```

**Argon2id의 옵션들**

- memoryInput: 입력한 메모리 비용
- memory: 계산된 메모리 비용 (빌더는 이것 대신 위 파라미터를 입력하게 함.)
  - memoryInput이 없을 때 m ≥ 93750 ÷ ((3 × t − 1) × α)  (단위: kB)
- saltLength: 솔트 길이. 기본 값: 16 Byte
- hashLength: 해시 길이. 기본 값: 32 Byte
- parallelism: 병렬성. 기본값: 1
- iterations: 반복성. t ≥ 1
- alpha: m ≲ 64 MiB일 때 α ≈ 95% 범위를 추천. m이 충분히 크면 α를 감소시켜도 됨.
- gain: 메모리 비용 계수(증폭비)

### Argon2d

```java
var factory = new PasswordEncoderFactory();
var option = Argon2dPasswordEncoderOption.fromDefaultBuilder()
        .gain(3f)
        .build();

var bcryptPasswordEncoder = factory.create(option);
```

**Argon2d의 옵션의 항목 종류는 Argon2id와 같습니다.**

## 패스워드 인코더 객체 캐싱

패스워드 인코더 인스턴스를 캐싱합니다. ([Caffeine Cache](https://github.com/ben-manes/caffeine) 기반 팩토리)  
같은 옵션을 사용하면 캐싱된 인스턴스를 제공합니다.
옵션 객체들의 참조가 달라도 내용이 서로 같으면 같은 옵션으로 인식합니다.

**기본 생성자를 사용한 캐시 만료 기본값**

```java
var factory = new PasswordEncoderFactory();
```

- 마지막 접근으로부터 최대 10분 동안 살아 있습니다.
- 최대 100개를 보존합니다.

**Builder 사용으로 캐시의 만료 설정을 커스텀하기**

```java
var factory = PasswordEncoderFactory.builder()
        .expireAfterAccess(3, TimeUnit.MINUTES)
        .maximumSize(3)
        .removalListener((key, value, cause) ->
                log.info("Cache entry for key {} was removed because of: {}", key, cause)
        )
        .build();
```

- expireAfterAccess(duration), expireAfterAccess(duration, TimeUnit)
    - 마지막 조회로부터 해당 시간 후 만료됩니다.
- expireAfterWrite(duration), expireAfterWrite(duration, TimeUnit)
    - 생성으로부터 해당 시간 후 만료됩니다.
- maximumSize: 최대 저장 개수
- maximumWeight: 최대 저장 용량
- removalListener((k, v, cause) -> {}): 요소 삭제 이벤트 리스너를 추가할 수 있습니다.

### 메서드 간 공유되는 캐시

지금 데모 버전에서 사용하는 패스워드 인코더 인스턴스는 모두
`PasswordEncoder` 인터페이스와 `CustomSaltingPasswordEncoder`를 동시에 구현합니다.  
같은 옵션을 사용하여 패스워드 인코더 인스턴스를 생성하면, 다음 두 메서드는 같은 인스턴스를 캐싱하여 반환합니다.

```java
var factory = new PasswordEncoderFactory();
var argon2IdOption = Argon2idPasswordEncoderOption.fromDefaultBuilder()
        .gain(3f)
        .build();

// 같은 옵션을 사용하여 생성하면, 다음 두 메서드는 같은 인스턴스를 캐싱하여 반환합니다.
var passwordEncoder = factory.create(argon2IdOption);
var customSaltingPasswordEncoder = factory.createCustomSaltingEncoder(argon2IdOption);
```

```kotlin
// 다음 결과를 기대할 수 있습니다.
assertSame(passwordEncoder, customSaltingPasswordEncoder)
```

# Properties (Not Automatically Applied)

다음 프로퍼티 클래스의 객체를 빈으로 주입받아 사용할 수 있습니다.

- `letsdev.core.password.property.PasswordEncoderProperties`

이 구성 속성은 다음 항목들을 자동으로 바인딩할 수 있습니다. 모든 속성은 선택 사항입니다.

```yaml
# 작성된 것은 기본값입니다. 모든 속성은 선택적입니다.
letsdev.password:
    default-encoder: bcrypt
    bcrypt:
      strength: 10
    argon2:
      mode: argon2id
      salt-length: 16 # Unit: Bytes
      hash-length: 32 # Unit: Bytes
      parallelism: 1
      # memory-input: # 생략 시 자동으로 계산됩니다. 메모리 비용 m ≥ 93750 ÷ ((3 × parallelism − 1) × α)
      iterations: 1
      alpha: 0.95
      memory-gain: 1.0
```

## Default Encoder Property

기본 인코더를 설정할 수 있습니다. 이 버전에서 지원하는 목록은 다음과 같습니다.

- `bcrypt`
- `argon2`
- `argon2d`
- `argon2i`
- `argon2id`

## Bcrypt Encoder Properties

- `strength`: 𝐒𝐭𝐫𝐞𝐧𝐠𝐭𝐡 = 𝐥𝐨𝐠₂(𝐫𝐞𝐩𝐞𝐭𝐢𝐭𝐢𝐨𝐧𝐬)  
  반복 인자를 말합니다. Cost factor 또는 work factor 등으로도 부릅니다.

## Argon2 Encoder Properties

argon2 최종 memory cost는 `memory-input`과 `memory-gain`의 곱입니다.

- `mode`
  - Argon2의 세 버전(`argon2d`, `argon2i`, `argon2id`) 중 하나입니다.
  - 기본값은 `argon2id`입니다.
- `salt-length`
- `hash-length`
- `parallelism`
- `iterations`
- `alpha`
  - 기본 목적은 `memory-input` 속성을 자동으로 계산할 때만 사용됩니다.
  - 0 초과 1 이하의 값을 권하며, 적어도 0을 초과해야 합니다.
  - Memory cost ≲ 64 MiB일 때 α ≈ 0.95 정도를 권합니다.
  - Memory cost 값이 충분히 크면, 더 작은 α 값을 사용할 수 있습니다.
- `memory-input`  
  이 속성을 입력하면 `alpha` 속성을 무시합니다.
- `memory-gain`  
  메모리 비용에 대한 증폭계수입니다.