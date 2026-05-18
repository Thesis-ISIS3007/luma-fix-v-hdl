# Thesis prototype

## Mill tasks

- `./mill compile`
- `./mill -w compile`
- `./mill run`
- `./mill test` — Scala/Chisel tests only (skips `Validation`- and `Samples`-tagged specs that need SW hex images)
- `LUMAFIXV_VALIDATION=1 ./mill test` — include validation programs from `test/resources/validation/`
- `LUMAFIXV_SAMPLES=1 ./mill test` — include baremetal sample programs from `test/resources/samples/`
- `LUMAFIXV_VALIDATION=1 LUMAFIXV_SAMPLES=1 ./mill test` — full SW hex suite
- `./scripts/run_tests.sh` — default suite; use `--with-validation` / `--with-samples` or env vars above
- `./scripts/run_validation_tests.sh` — same as `LUMAFIXV_VALIDATION=1 ./mill test`
- `./scripts/run_samples_tests.sh` — same as `LUMAFIXV_SAMPLES=1 ./mill test`
- `./mill test -D luma.validation=1` — same as `LUMAFIXV_VALIDATION=1`
- `./mill test -D luma.samples=1` — same as `LUMAFIXV_SAMPLES=1`
- `./mill test -l Validation` — exclude validation specs explicitly
- `./mill test -l Samples` — exclude sample specs explicitly
- `./mill test -DemitVcd=1`
- `./mill mill.scalalib.scalafmt/reformatAll`
- `./mill mill.scalalib.scalafmt/checkFormat`
- `./mill mill.scalalib.scalafmt/checkFormatAll`
- `./mill __.fix`
- `./mill fix --check src`
