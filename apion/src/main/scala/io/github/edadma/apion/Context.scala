package io.github.edadma.apion

/** A type-safe key into a [[Context]].
  *
  * The phantom type `A` records what a key maps to, so lookups return `Option[A]`
  * with no casting at the call site. Keys are compared by identity (not by name),
  * so define each key exactly once — typically a `val` in a middleware's companion
  * object — and share that instance between the code that writes it and the code
  * that reads it.
  */
final class TypedKey[A](val name: String):
  override def toString: String = s"TypedKey($name)"

/** An immutable, heterogeneous, type-safe map from [[TypedKey]] to values.
  *
  * This is the per-request extension store: middleware attach data under their own
  * keys and downstream handlers read it back with the value's static type recovered.
  * The only cast lives in [[get]] and is safe by construction, because a value can
  * only be stored under a `TypedKey[A]` via [[updated]], which requires an `A`.
  */
final class Context private (private val entries: Map[TypedKey[?], Any]):
  def get[A](key: TypedKey[A]): Option[A] = entries.get(key).map(_.asInstanceOf[A])

  def apply[A](key: TypedKey[A]): A =
    get(key).getOrElse(throw new NoSuchElementException(s"no value for context key '${key.name}'"))

  def contains(key: TypedKey[?]): Boolean = entries.contains(key)

  def updated[A](key: TypedKey[A], value: A): Context = new Context(entries.updated(key, value))

  def removed(key: TypedKey[?]): Context = new Context(entries - key)

  def isEmpty: Boolean = entries.isEmpty

  override def toString: String = entries.keys.map(_.name).mkString("Context(", ", ", ")")

object Context:
  val empty: Context = new Context(Map.empty)
