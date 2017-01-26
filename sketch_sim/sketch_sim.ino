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
            Serial.println(inData);
            Serial.println(inData.length());
            if(inData.startsWith("O")){
              Serial.println("A");
              delay(2000);
              Serial.println('E' + inData.substring(1,3));
            }
        }
    }
}
