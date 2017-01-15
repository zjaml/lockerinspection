# Prerequisite
Android device need to support OTG mode.

# Install
Build source with Android Studio.

# How to use
<img src="images/1.jpg" width="300px">
## connect the board
* Open the app and connect the board. 
* Grant USB permission to the app.
* The button will be enabled when connection is established.

## Log Area
 show command issued and response from the board.
## Check in Command
Input door number and press C-IN button.
## Check out command
Input door number and press C-OUT button.
## Door state query
* Input door number and press DOOR button.
* if door number is empty, door state query for all boxes will be issued.

## Empty state query
* Input door number and press EMPTY button.
* if door number is empty, state query for all boxes will be issued.

## Charging control
The app monitors battery level and charging state of the device, and issues command to board to start/stop charging.
* When battery level goes below 15%, Command: LOW to start charging
  * The app will continue to monitor charging state, and issue LOW command periodically until the device is being charged.
* When battery goes above 95%, Command: HIGH to stop charging
  * The app will continue to monitor charging state, and issue HIGH command periodically until the device is being discharged.
* press CHARGE/DISCHARGE to simulate the LOW/HIGH command, making test easier.