# jfr-native-image-tests
The goal of this repository is to provide a test suite is to test JFR events in native images. It helps to run multiple tests at once and
automatically analyze output JFR files. This is useful for test driven development, as well as regression testing.

### Usage:
1. Compile to bytecode.
   
    `mvn clean package`


2. Compile `com.redhat.ni.tester.Tester` class into a native image.
   
    `native-image -H:+AllowVMInspection -cp </path/to/jfr-native-image-tests>/target/classes com.redhat.ni.tester.Tester`


3. Run the generated executable.
   
    `./com.redhat.ni.tester.tester`

Exceptions will be thrown where tests fail.

### Creating new tests
Add new event tests under the "events" directory. All tests should extend `com.redhat.ni.tester.Test` class.
To include your new test in the test harness, add it in the `com.redhat.ni.tester.Tester.loadTests()` method.
 Generally, each test should run code that triggers the target event/scenario, then analyze
the output JFR snapshot to ensure the desired behaviour occurred. 
