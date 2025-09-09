# Jedis OOM
> A simple showcase of buffer corruption in Jedis when OutOfMemoryError occurs.

## How to reproduce
Just run the program with the following vm options:
```
-Xmx20m -Xms20m -XX:+HeapDumpOnOutOfMemoryError
```
The issue is not guaranteed to happen on every run, but it should happen quite often.
