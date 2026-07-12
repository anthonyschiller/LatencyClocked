['module-one', 'module-two'].each { moduleName ->
  def targetDirectory = new File(basedir, "${moduleName}/target")
  def jars = targetDirectory.listFiles().findAll {
    it.name ==~ /${moduleName}-.*\.jar/ &&
        !it.name.endsWith('-sources.jar') &&
        !it.name.endsWith('-javadoc.jar') &&
        !it.name.endsWith('-tests.jar')
  }
  assert jars.size() == 1

  def jar = new java.util.jar.JarFile(jars[0])
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
