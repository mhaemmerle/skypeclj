# skypeclj

A simple Clojure wrapper for the Skype Java API

Note: The wrapper is currently conflated with a bot/logger and will be spun out eventually to a standalong project so you can have the wrapper without any of the other potentially unnecessary stuff.

## Requirements

This [guide](http://dow.ngra.de/2012/01/06/skype-bot-for-fun-and-profit-part-i-getting-started) by Toomas Römer will get you started.

In summary, you should have done the following steps before you can start using skypeclj.

-------------------------------------------------------------------------------

Joined the Skype Developer Program:

[Skype Developer Program](http://developer.skype.com)

-------------------------------------------------------------------------------

Downloaded the Skype SDK - called SkypeKit - that contains headless runtime, language bindings and API's for C, Java and Python.

-------------------------------------------------------------------------------

Built the Java API (assuming you are in the directory where you unpacked SkypeKit to):

```
$ cd interfaces/skype/java2
$ ant
```

-------------------------------------------------------------------------------

Installed the compiled jar to your local maven repository:

```
$ mvn install:install-file -Dfile=skypekit.jar -DgroupId=com.skype -DartifactId=skypekit -Dversion=1.0 -Dpackaging=jar
```

-------------------------------------------------------------------------------

Downloaded a developmaint keypair from Skype and converted it from base64 PEM to a binary DER format:

```
$ openssl pkcs8 -topk8 -in my-skype-key.pem -outform DER -out my-skype-key.der -nocrypt
```

-------------------------------------------------------------------------------

Have a normal Skype account with credentials ready.


## Usage

The runtime will stop every time you disconnect your client, so running in a loop can save you some time on each development cycle, as it will be restarted once control returns:

```
$ while :; do ./mac-x86-skypekit-novideo; sleep 1; done
```

Edit `resources/config.clj` and fill in Skype username, password and the path to the converted key file.

```
$ lein deps
$ lein trampoline run
```


## License

skypeclj is Copyright © 2012 Marc Haemmerle

Distributed under the Eclipse Public License, the same as Clojure.
