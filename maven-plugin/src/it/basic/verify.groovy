def index = new File(basedir, 'target/classes/META-INF/latency-clocked/index')
assert index.isFile()
assert index.text.contains('com.example.InvokerTimedSample')

def report = new File(basedir, 'target/latency-clocked/instrumentation-report.txt')
assert report.isFile()
assert report.text.contains('it.sample')
