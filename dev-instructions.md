- follow the TDD methodology
    - unit testing for Apion should just be at the request handler level, not through ports)
- only depend on Node.js and Scala.js libraries
- simple, not overly complex
- similar to Express.js
- sbt multi-project containing two sub-projects
    - Apion library
    - Node.js typings
- any addition to Apion that uses something from Node.js must accompany an appropriate addition to the Node.js typings