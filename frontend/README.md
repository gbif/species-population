# backend
mvn package
java -jar target/tile-server-1.0-SNAPSHOT-shaded.jar server ./server.conf

# frontend
npm install
gulp