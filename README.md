# CloudyGame Thin Client - Android version

This is the Android version of the [thin client](https://github.com/bloodelves88/CloudyGameThinClient) for [CloudyGame](https://github.com/nus-mmsys/UnrealEngine/tree/4.16).

## Configuration

Create a config file, ```cloudy.conf``` in your ```app/src/main/res/raw/``` directory. 

Set your host IP by filling in the config file as follows. Note that neither the ```http://``` prefix nor port number should be included.

```
ip=your-host-ip.com
```
### Optional Settings

The following settings are optional.

| Setting     | Type    |  Default    | Description                       |
|-------------|---------|-------------|-----------------------------------|
| streamPort0 | int     | ```30000``` | video stream port of first player |
| signalPort  | int     | ```55556``` | port to send join/quit signals    |
| ctrlPort    | int     | ```55555``` | port to send game actions         |
| gameId      | int     | ```1```     | game ID (not used)                |
| sessionId   | int     | ```1```     | session ID (not used)             |
| width       | int     | ```1280```  | width of the video stream         |
| height      | int     | ```720```   | height of the video stream        |
| showLog     | boolean | ```false``` | to turn log on and off            |
