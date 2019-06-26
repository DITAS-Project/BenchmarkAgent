# DITAS - Benchmarking Agent

The Benchmarking Agent (BA) is a lightweight utility that can perform http based stress tests against vdcs. The BA is strongly coupled to the (DITAS Benchmarking Scheduler)[https://github.com/DITAS-Project/BenchmarkScheduler].
## Getting Started

This project is based on Gradle, Kotlin. 

### Prerequisites

OpenJDK 11, Gradle 4+

### Installing
Run `gradle build` to compile,
`gradle dockerBuildImage` to build the latest docker image.

Both build and dockerBuildImage will run all tests before completing.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/your/project/tags). 

## License

This project is licensed under the Apache 2.0 - see the [LICENSE.md](LICENSE.md) file for details.

## Acknowledgments

This is being developed for the [DITAS Project](https://www.ditas-project.eu/)
