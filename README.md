# Boostrap method for `copy`

> An exploration of using `invokedynamic` for Kotlin data classes.

The `invokedynamic` opcode was introduced in Java 7 to support lambdas and dynamic languages.
More recently, it has become the way Java "implements" several functions for records, such
as `toString` and `equals`, without having to generate loads of code for each record.
So why not use the same technique for our data classes?

We strongly recommend looking at [this post](https://www.baeldung.com/java-invoke-dynamic)
to understand how `invokedynamic` works. The key point is to create a _boostrap method_ that
generates the required code, which is then linked as if it had always been there.
The `DataCopy.kt` file in this repository shows that generating such a bootstrap method for
data classes is **feasible**.

## Practical usage

Once this bootstrap method is available in the standard library, whenever we find a call
of the form:

```kotlin
foo.copy(bar = "bye")
```

we should use `invokedynamic` to link the corresponding implementation. The arguments to
the boostrap method are a reference to the (data) class for which `copy` is called, and
the names of the properties which are given to the call.

```
invokedynamic kotlin/internal/Bootstrap.copy Foo::class "bar"
```

This technique solves the **forward compatibility** problem of data classes.
The `invokedynamic` instruction only requires the name of the properties to modify at
compile time, but knowing what are all the properties needed to call the constructor is
deferred until runtime.