$version: "2"

namespace smithy4s.hello

use smithy4s.api#simpleRestJson

@simpleRestJson
service HelloWorldService {
  version: "1.0.0",
  operations: [Hello, Hello2]
}

@http(method: "POST", uri: "/hello/{name}", code: 200)
operation Hello {
  input := {
    @httpLabel
    @required
    name: String,

    @httpQuery("town")
    town: String    
  },
  output := {
    @required
    message: String    
  }
}

@http(method: "POST", uri: "/hello2/{name}", code: 200)
operation Hello2 {
  input := {
    @httpLabel
    @required
    name: String,

    age: Integer,

    @httpQuery("town")
    town: String    
  },
  output := {
    @required
    message: String    
  }
}