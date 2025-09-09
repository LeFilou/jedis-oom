# Jedis OOM
> A simple showcase of buffer corruption in Jedis when OutOfMemoryError occurs.

## How to reproduce
1. Start a Redis Cluster using docker compose:
```bash
docker compose up -d
```
2. Run the Java program:

```
mvn clean package
java -Xms32m -Xmx96m -XX:MaxGCPauseMillis=100 -jar target/jedis-oom-1.0-SNAPSHOT.jar
```

## Note
The issue is not guaranteed to happen on every run, but it should happen quite often. You should see logs like this:
```
INFO  [c.l.j.RedisWorker:95] (pool-2-thread-64) - Issue found on the value! readValue=thread-31-large-value-1-{
  "key1": "This is a large value used to simulate a large JSON object"
}
, expectedValue=thread-5-large-value-1-{
    "key1": "This is a large value used to simulate a large JSON object"
}
```