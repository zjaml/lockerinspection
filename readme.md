# Dependency
This project depend on LockerManager library which it assumes to be located in ../LockerManager directory. If the LockerManager is not cloned yet, it needs to be cloned first.

# Actions
* Start
* Proceed
    * when previous door closed
    * can proceed with error
* Cancel
* Report Area
    * issue list

# Inspection sequence for manufacturer
* prepare 6 types of object.
* check in 6
* checkout 6
* perform above sequence 5 times until all boxes has been tested
    * object detect every step
    * door close state every step

# Simulator program on arduino side.
* Send Ack back when received checkin/checkout command
* Send Door close event back after a short period
* Create some exception empty status event.


# Issues:
OnReceiveData event receives byte array which are not be delimited by line break.
