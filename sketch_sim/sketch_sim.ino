// Buffer to store incoming commands from serial port
String inData;

void setup() {
    Serial.begin(9600);
//    Serial.println("Waiting for Raspberry Pi to send a signal...\n");
}

void loop() {
    while (Serial.available() > 0)
    {
        char recieved = Serial.read();      
        inData += recieved; 

        // Process message when new line character is recieved
        if (recieved == '\n')
        {
            if(inData.startsWith("O")){
              Serial.print("A\n");
              delay(3000);
              if(inData.substring(3,4)=="T"){
                Serial.print('F' + inData.substring(1,3) + '\n');
              }else{
                Serial.print('E' + inData.substring(1,3) + '\n');
              }
            }
            inData = "";
        }
    }
}
