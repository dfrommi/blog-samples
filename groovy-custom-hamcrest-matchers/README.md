---
posted: 2015-08-15
tags: [groovy, hamcrest, testing]
---

# Groovier custom Hamcrest Matchers

Custom Hamcrest Matchers help quite a bit in simplifying unit tests and they are fairly easy to implement. The interface contains only 3 methods, 2 of them to create a textual description of expected and actual parameters and one do perform the verification. Implementing an interface is not difficult at all, but a lot of boilerplate code is required just to make that happen. I wanted to see how much this can be simplified using Groovy and the answer is *quite a lot*.

I assume that you are already familiar with Hamcrest, Matchers and how to use them. Let's just have a quick look on how to implement a custom Matcher. 

Matchers have 3 parts:

- The value verification
- Textual description of expected value
- Textual description of value that Matcher has received

Hamcrest ships with several abstract base classes that help implementing custom matchers. They differ slightly in type safety and method signatures. Following examples are using `org.hamcrest.TypeSafeMatcher<T>` with methods `matchesSafely`, `describeTo` and `describeMismatchSafely`, but other base classes would work equally well. You'll see it in action in just a minute, but first let's prepare a test scenario. 

```groovy
class Demo {
    int number
}

Demo demo = new Demo(number: 42)
assertThat demo, hasNumber(41)
```

The `Demo` class has a `number` property and we are writing a matcher `hasNumber` that is verifying the `number` property of `Demo` instances. The code above raises an assertion error on purpose, because you wouldn't see anything if the test was successful. Output looks like that

```
Caught: java.lang.AssertionError: 
Expected: a Demo object with number <41>
     but: was <42>
```

Let's first look at the straight forward approach of Matcher implementation. Method `hasNumber` returns an anonymous inner class implementing `TypeSafeMatcher`. 

```groovy
def hasNumber(int i) {
    new TypeSafeMatcher<Demo>() {
        @Override
        protected boolean matchesSafely(Demo item) {
            i == item.number
        }

        @Override
        void describeTo(Description description) {
            description.appendText("a Demo object with number ").appendValue(i)
        }

        @Override
        protected void describeMismatchSafely(Demo item, Description mismatchDescription) {
            mismatchDescription.appendText("was ").appendValue(item.number)
        }
    }
}
```

It's not very Groovy, except for some syntactic simplifications like missing semicolons and return statements. Every Java developer would be able to understand that code (that's a good thing), but it also has a lot of boilerplate code, so on to second round.

In Groovy, interfaces and abstract classes can be implemented using maps. Keys are the method names, values are implementing the methods using Closures and then the Map is typecasted to interface or class type. Let's try that.

```groovy
def hasNumber(int i) {
    [ matchesSafely: { i == it.number },
      describeTo: { it.appendText("a Demo object with number ").appendValue(i) },
      describeMismatchSafely: { item, descr -> descr.appendText("was ").appendValue(item.number) }
    ] as TypeSafeMatcher<Demo>
}
```

Wow, what an improvement. It's short and concise without any glue code around.

From interface implementation side there is hardly any room for improvement, so let's look at the method bodys. Hamcrest's `Description` class uses `appendText` and `appendValue` to create the textual description, which is okay. In Groovy we would mostly use operators for that, so let's do it, just because we can. Left-shift operator `<<` can be an alias for `appendText` and or-operator `|` can alias `appendValue`.

```groovy
Description.metaClass.leftShift = { text ->
    delegate.appendText(text.toString())
}

Description.metaClass.or = { value ->
    delegate.appendValue(value)
}

def hasNumber(int i) {
    [ matchesSafely: { i == it.number },
      describeTo: { it << "a Demo object with number " | i },
      describeMismatchSafely: {item, descr -> descr << "was " | item.number }
    ] as TypeSafeMatcher<Demo>
}
```

I would usually use an Extension Module instead of `metaClass`, because I want it to be available everywhere without taking case that `metaClass` code has been executed before for usage of operators. But for this example `metaClass` is fine.

Map based implementation of interfaces and abstract classes can be simplified even further if the interface has only one method or likewise if the abstract class has only one abstract method. In that case, you can a Closure and typecast that to interface or class type. No map or method name required. That approach can't be applied here, because we have to implement 3 methods. But we could create our own subclass of `TypeSafeMatcher` that is implementing those 3 methods by delegating to one new abstract method doing all the work.

```groovy
abstract class GroovyMatcher<T> extends TypeSafeMatcher<T> {
    private Description description = new StringDescription()
    private Description mismatchDescription = new StringDescription()

    @Override
    boolean matchesSafely(T item) {
        match(item, description, mismatchDescription)
    }

    @Override
    void describeTo(Description description) {
        description << this.description
    }

    @Override
    void describeMismatchSafely(T item, Description mismatchDescription) {
        mismatchDescription << this.mismatchDescription
    }

    abstract boolean match(T item, Description description, Description mismatchDescription)
}

def hasNumber(int i) {
    { item, d, md ->
        int n = item.number
        d <<  "a Demo object with number " | i
        md << "was " | n
        i == n
    } as GroovyMatcher<Demo>
}
```


`GroovyMatcher` has only one abstract method `match`. The implmentation has to set both descriptions and then return verification result. Descriptions are held in class fields until used by methods `describeTo` and `describeMismatchSafely`. Method `match` is only called by `matchesSafely` to avoid 3 calls of `match` in case of a verification error. That works because `matchesSafely` is always called first. One drawback is that the implementation of match always has to create all description values, even if they are not used later on (if verification was successful).

With `GroovyMatcher` at hand, we can follow the one-Closure approach and just implement the Matcher by typecating a Closure to `GroovyMatcher`.

We have seen 4 different ways of implementing the same matcher. So which one is the best?  
I would not use the classic way for on-the-fly matchers. There's just too much boilerplate code. IDEs can create that for you, but it's still there and distracts from real code.  
The first Map-based approach is very short and concise and unlike last 2 solutions, it doesn't require extra code outside of the factory method. Existence of `appendText` and `appendValue` are after all not really a problem.  
Map-based approach with meta-programming is my favorite. It's short, concise and very simple. The extra methods on `Description` can be put as Extension Module in an extra jar. Then they are available by just adding a dependency to the project.  
Finally the one-Closure solution. I think it doesn't make life easier, though it's only one method that has to be implemented. But verification is not always as easy as in our example and then one method confuses more than it helps.

The winner for me is number 3: Map-based solution with meta-programming

Which one would you choose and why? Or do you know other ways of implementing custom Matchers? Then please leave a comment.