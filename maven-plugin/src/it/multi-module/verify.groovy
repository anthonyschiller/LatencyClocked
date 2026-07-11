['module-one', 'module-two'].each { moduleName ->
  def jar = new java.util.jar.JarFile(new File(basedir, "${moduleName}/target/${moduleName}-1.0-SNAPSHOT.jar"))
  try {
    def entry = jar.getJarEntry('META-INF/latency-clocked/index')
    assert entry != null
    def text = jar.getInputStream(entry).getText('UTF-8').trim()
    assert text
    assert !text.contains('|')
  } finally {
    jar.close()
  }
}

assert new File(basedir, 'module-one/target/latency-clocked/instrumentation-report.txt').isFile()
assert new File(basedir, 'module-two/target/latency-clocked/instrumentation-report.txt').isFile()
