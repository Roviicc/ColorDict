# Engine benchmark

Measures what a large dictionary costs the StarDict engine: index load time,
heap held, and per-lookup latency including the dictzip read and HTML render.
Results and their interpretation live in [../../docs/DICTIONARY-DATA.md](../../docs/DICTIONARY-DATA.md).

```bash
# 1. Build a synthetic dictionary (150k entries, dictzip-compressed)
python3 tools/bench/gen_big.py 150000 /tmp/bench-150k dz

# 2. Compile the engine and the benchmark
javac -d /tmp/bench-engine $(find app/src/main/java/io/github/roviicc/colordict/engine -name '*.java')
javac -cp /tmp/bench-engine -d /tmp/bench-classes tools/bench/Bench.java

# 3. Run with a heap cap that approximates a phone
java -Xmx512m -cp "/tmp/bench-engine:/tmp/bench-classes" Bench /tmp/bench-150k/big.ifo
```

Sample output:

```
bench-150k   words=  150,000  load=    76 ms  heap=    8 MB  lookup+read=0.088 ms avg (2000 reads)
```

The generated articles are deliberately repetitive, so dictzip compresses them
far better than real dictionary prose would. Use the entry count, load time,
heap and latency figures; size real `.dict.dz` files from real data.
