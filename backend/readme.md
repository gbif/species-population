## A simple protobufer tile server

To build: ```mvn clean package```

To run: ```java -jar target/datacube-tile-server-1.0-SNAPSHOT-shaded.jar server server.conf```

To view: ```http://localhost:8080/0/0/0/datacube.pbf```

The URL format is ```/{z}/{x}/{y}/density.pbf```  
