## SSH Tunnel for Android System ##

SSHTunnel is a SSH tunnel app for Android System, based on [Connectbot](http://code.google.com/p/connectbot/) and Dropbear / OpenSSH (Beta Branch). With this app and a configured server (typically configured with sshd and nginx / squid), you can easily browse internet through a SSH tunnel on your android devices.



![http://sshtunnel.googlecode.com/files/ssh-tunnel-diagram-ht.jpg](http://sshtunnel.googlecode.com/files/ssh-tunnel-diagram-ht.jpg)


SSHTunnel is using **redsocks** (http://darkk.net.ru/redsocks/) to redirect all traffic on Android. You can check out its source codes from: https://github.com/darkk/redsocks

Currently, the latest sshtunnel source codes can be found here: https://bitbucket.org/madeye/sshtunnel and the latest sshtunnel-beta can be found here: https://github.com/madeye/sshtunnel-beta

## Notice ##

If you want to **set up your own VPS** to work with this app, please install and configure **HTTP PROXY** on your VPS first (typically squid or nginx). To support HTTPS (SSL), you must configure your http proxy to allow CONNECT Method on 443 port

Considering the poor performance of dynamic port forwarding on most android devices, we suggest you to use a transparent proxy set up in the SSH server and use local port forward to proxy data through SSH tunnel.

To work with your private/public key, please store your key (only OpenSSH format, not putty) as the file /sdcard/sshtunnel/key

## If you run into application problems ##

Please, please send us relevant logcat dumps when you have a crash. Here's how to get a logcat dump:

1. Enable USB debugging. Go into Settings, Applications, Development, and enable the "USB debugging" option.

2. Install the Android SDK. You'll need a desktop tool called adb that will help you get error logs.

3. Make sure your phone can connect. Follow the instructions here to make sure that adb can talk with your device:

http://code.google.com/android/intro/develop-and-debug.html#developingondevicehardware

3. Dump logcat data. From your desktop console, type ./adb -d logcat | grep -i SSHTunnel. Make sure it's showing some data, then copy everything into a text file and attach to your bugreport here on this site. CAREFULLY read over the logs for any sensitive information BEFORE posting. You might need to Ctrl+C to quit adb once it stops printing data.