package prototype

import org.scalatest.funspec.AnyFunSpec

import chisel3._
import chisel3.simulator.scalatest.ChiselSim

class RegFileSpec extends AnyFunSpec with ChiselSim {
  describe("RegFile") {
    it("writes and reads architectural registers") {
      simulate(new RegFile()) { c =>
        c.io.rdWrite.poke(true.B)
        c.io.rdAddr.poke(1.U)
        c.io.rdData.poke("h12345678".U)
        c.clock.step()

        c.io.rs1Addr.poke(1.U)
        c.io.rs2Addr.poke(0.U)
        c.clock.step()

        c.io.rs1Data.expect("h12345678".U)
        c.io.rs2Data.expect(0.U)
      }
    }

    it("keeps x0 hardwired to zero") {
      simulate(new RegFile()) { c =>
        c.io.rdWrite.poke(true.B)
        c.io.rdAddr.poke(0.U)
        c.io.rdData.poke("hFFFFFFFF".U)
        c.io.rs1Addr.poke(0.U)
        c.io.rs2Addr.poke(0.U)
        c.clock.step()

        c.io.rs1Data.expect(0.U)
        c.io.rs2Data.expect(0.U)
      }
    }
  }
}
