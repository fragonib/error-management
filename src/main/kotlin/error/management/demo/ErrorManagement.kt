package error.management.demo

import arrow.core.Either
import arrow.core.Option
import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.continuations.either
import arrow.core.getOrElse

/**
 * Model
 */
data class Person(val name: String)
data class Address(val country: Country)
data class Country(val code: Code)

@JvmInline
value class Code(val value: String)

/**
 * Use case:
 *
 * Access the country code of a person's address through a remote service
 */
suspend fun Person.address(): Either<AddressStatus, Address> =
        Either.Left(AddressStatus.ServiceIsDown)

/**
 * When accessing the address we may get back instead an AddressStatus
 */
sealed class AddressStatus {
    object Relocating : AddressStatus()
    object Missing : AddressStatus()
    object ServiceIsDown : AddressStatus()

    override fun toString(): String {
        return this.javaClass.simpleName
    }
}

/**
 * [RuntimeException].
 * Good: `Code` is a simple return type
 * Bad: Caller does not know this function throws
 * Ugly: Can crash your application
 */
suspend fun Person.countryCodeExceptions(): Code =
        address()
                .map { it.country.code }
                .getOrElse { throw RuntimeException(it.toString()) } // Costly to create stack trace

/**
 * [Option]
 * Good: Caller needs to handle
 * Bad: We loose info of why address is not there,
 *      Caller needs to deal with boxed types
 * Ugly: New allocations for composition
 */
suspend fun Person.countryCodeOption(): Option<Code> =
        address()
                .map { it.country.code }
                .orNone()

/**
 * [Either]
 * Good: Caller needs to handle, we recover info why address may not be there
 * Bad: Caller needs to deal with boxed types
 * Ugly: New allocations for composition
 */
suspend fun Person.countryCodeEither(): Either<AddressStatus, Code> =
        address().map { it.country.code }

/**
 * [Effect] [Either] DSL
 * Good: Caller needs to handle, we recover info why address may not be there,
 * better monadic syntax
 * Bad: Caller needs to deal with boxed types
 * Ugly: Explicit DSL either block
 */
suspend fun Person.countryCodeEitherDSL(): Either<AddressStatus, Code> =
        either { address().bind().country.code }

/**
 * [Effect]
 * Good: Caller needs to handle, direct syntax
 * Bad?: We are back to a form of typed exceptions
 * Ugly?: Need to learn context receivers.
 */
context(EffectScope<AddressStatus>)
suspend fun Person.countryCode(): Code =
        address().bind().country.code

/**
 * If we redefined our `address` function to use `Raise`
 */
context(EffectScope<AddressStatus>)
suspend fun Person.addressThatSignalAProblem(): Address =
        shift(AddressStatus.ServiceIsDown) // Stack trace disabled, may be enabled with config

/**
 * Then we don't need to `bind`
 */
context(EffectScope<AddressStatus>) /* Dependencies */
suspend fun Person /* Receiver */.countryCodeRaised(): Code /* <-- Simple return */ =
        addressThatSignalAProblem().country.code /* <-- Direct syntax, no bind */

suspend fun main() {

    val person = Person("Fran")

    val program: Result<Code> = runCatching { person.countryCodeExceptions() }
    println(program)  // Failure(java.lang.RuntimeException: ServiceIsDown)

    val programOption: Option<Code> = person.countryCodeOption()  // we don't know why it failed
    println(programOption) // Option.None

    val programEither: Either<AddressStatus, Code> = person.countryCodeEither() // we have to deal with boxes
    println(programEither) // Either.Left(ServiceIsDown)

    val programEitherDsl: Either<AddressStatus, Code> = person.countryCodeEitherDSL() // we have to deal with boxes
    println(programEither) // Either.Left(ServiceIsDown)

    // Effects are controlled as a function until the edge.
    // At that point they can be folded to a terminal value
    val programEffect: Effect<AddressStatus, Code> = effect { person.countryCodeRaised() } // at the edge we wrap in effect
    println(programEffect.toEither()) // Left

}
