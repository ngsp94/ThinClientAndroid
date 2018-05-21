# CloudyGame Thin Client - Android version

This is the Android version of the [thin client](https://github.com/bloodelves88/CloudyGameThinClient) for CloudyGame.

## Configuration

Create a config file, ```cloudy.conf``` in your ```app/src/main/res/raw/``` directory. 

Set your host IP by filling in the config file as follows, with your own IP and port number.

```
ip=http://your-host-ip.com:8000
```
### Optional Settings

The following settings are optional.
- showLog: ```true/false```, to turn log on and off
- width: ```any integer```, the width of the video stream
- height: ```any integer```, the height of the stream
