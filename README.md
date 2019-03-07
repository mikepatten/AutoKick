# AutoKick
This is a program to automatically log users off of a 4690 controller after a set period of time (15 minutes by default)
to comply with PCI-DSS requirements.

Copy the .jar file to the F: drive and add it and F:\adxetc\java\lib\os4690.zip to the controller classpath for Java 6
then add the background task. Remember, Java 6 uses the Linux JVM so read the install page on the Wiki for instructions.
