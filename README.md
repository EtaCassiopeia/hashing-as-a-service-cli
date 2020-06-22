Hashing-as-a-Service-cli
========================

Hashing-as-a-Service-cli is a simple CLI application that converts each line of a text file into the corresponding hash value (By preserving the order). Under the hood, it uses an existing REST service to hash the lines.
For instance:

Given input.txt as follows:
```
a
b
…
```

The output.txt should be :
```
hash-of-a
hash-of-b
…
```

Thanks to the Work-Pulling pattern used to implement this application, it is able to process the large input file without getting any out-of-memory exceptions.

## How to compile and run

Run `sbt universal:packageBin` to generate a zip bundle of the project. After extracting the generated zip file which is located at './target/universal/hashing-as-a-service-cli-0.1.0-SNAPSHOT.zip` you can run the following command to compute the hash of the content of a text file:

```shell script
bin/hashing-as-a-service-cli <input-file-to-hash> <hashed-output-file>
``` 

Note: This application uses an existing REST service to compute the hash. Before using this application the aforementioned service should be up and running. Please refer to [project's documentation](https://github.com/EtaCassiopeia/hashing-as-a-service) for more details.

## Design and Components

The main constraint in this project is using Scala and Akka Actors to solve the problem. To make it a bit interesting I've decided to combine it with ZIO and functional effects.
ZIO is a library for asynchronous and concurrent programming. Some components of this project are implemented using ZIO. ZIO is able to describe the side-effects in a functional and composable way and later run them asynchronously. 
It totally serves our purpose because the application has to make Http calls and needs to perform some IO operations. Besides ZIO, Akka is extensively used in this project to make a concurrent application. 

### Master Slave work pulling pattern

Each Akka actor is able to process one message at a time and the rest of the messages get enqueued into the mailbox. If the code inside a given actor takes time, it's likely that the messages sent to that actor will pile up in the mailbox. If the mailbox type is unbounded, this could lead to an out-of-memory error. If it is bounded, it will probably drop messages when it reaches the maximum size. 

To have better control over this kind of scenario, we can use the Master Slave work pulling pattern. This pattern lets a master actor control how many slaves can do work and distribute it among them. It only pushes work tasks to them when they are ready. Therefore, it is possible to manage the behavior of your system when all slaves are busy doing work. 
This application is design based on Work-Pulling pattern. `TaskMaster` distributes and controls the tasks and `TaskWorker`s are responsible for running the tasks.


### Pipe pattern

One of the most used patterns in Akka is the Pipe pattern which is to send the result of Future to another actor, upon completion of the Future. This pattern tightly coupled to Future. `ZioFutureOps` is used to convert ZIO data types to Future and adds an extension to `ActorRef` to pipe ZIO data types.

### Sequential file reading

The application is able to read the input file chunk-by-chunk. It's not possible to read fixed-size blocks and assign them to the workers, it might have some broken lines at the end of each block and the start position of each block depends on the end position of the previous one. Therefore, we need a way to process the read block requests one-by-one.
`IOHandler` is designed in such a way to read one block at a time. If it receives more than one request to read a block it stashed all of the other request and replay them later.

`IOHandler` is also responsible for merging the processed blocks. The start offset of each block is used as the taskId. This helps `IOHandler` to read the intermediate temp files with the correct order.

### TaskMaster

`TaskMaster` is the heart of the system which spawns workers and assign tasks to them. This component is responsible for following up on the assigned tasks and re-assign them in case of any failures.

### TaskWorker

Data blocks read by `IOHandler` are passed to the `TaskWorker`s as a new task. `TaskMaster` hashes the lines by making HTTP calls to the Hashing-as-a-service API. After receiving the results it stores them in a temp file (`/tmp-folder/jobId/taskId.tmp`).
In case of any failure, `TaskWorker` reports them to `TaskMaster`.
 