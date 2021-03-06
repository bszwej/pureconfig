package pureconfig

import com.typesafe.config._
import pureconfig.error._
import pureconfig.generic._
import pureconfig.generic.auto._

class CoproductHintSuite extends BaseSuite {

  behavior of "CoproductHint"

  {
    implicit val hint = new FieldCoproductHint[AnimalConfig]("which-animal") {
      override def fieldValue(name: String) = name.dropRight("Config".length)
    }

    it should "read values as expected when using a FieldCoproductHint" in {
      val conf = ConfigFactory.parseString("{ which-animal = Dog, age = 2 }")
      ConfigConvert[AnimalConfig].from(conf.root()) shouldEqual Right(DogConfig(2))
    }

    it should "write values as expected when using a FieldCoproductHint" in {
      val conf = ConfigConvert[AnimalConfig].to(DogConfig(2))
      conf shouldBe a[ConfigObject]
      conf.asInstanceOf[ConfigObject].get("which-animal") shouldEqual ConfigValueFactory.fromAnyRef("Dog")
    }

    it should "fail to read values that are not objects when using a FieldCoproductHint" in {
      val conf = ConfigValueFactory.fromAnyRef("Dog")
      ConfigConvert[AnimalConfig].from(conf) should failWith(
        WrongType(ConfigValueType.STRING, Set(ConfigValueType.OBJECT)))
    }

    it should "throw an exception when the hint field conflicts with a field of an option when using a FieldCoproductHint" in {
      sealed trait Conf
      case class AmbiguousConf(typ: String) extends Conf

      implicit val hint = new FieldCoproductHint[Conf]("typ")
      val cc = implicitly[ConfigConvert[Conf]]

      val conf = ConfigFactory.parseString("{ typ = ambiguousconf }")
      cc.from(conf.root()) should failWithType[KeyNotFound] // "typ" should not be passed to the coproduct option

      val ex = the[ConfigReaderException[_]] thrownBy cc.to(AmbiguousConf("ambiguousconf"))
      ex.failures.toList shouldEqual List(ConvertFailure(
        CollidingKeys("typ", ConfigValueFactory.fromAnyRef("ambiguousconf")), None, ""))
    }
  }

  {
    sealed trait Color
    case object RainyBlue extends Color
    case object SunnyYellow extends Color

    implicit val hint = new EnumCoproductHint[Color] {
      override def fieldValue(name: String) = ConfigFieldMapping(CamelCase, KebabCase)(name)
    }

    implicit val badHint = new EnumCoproductHint[AnimalConfig]

    it should "read values as expected when using an EnumCoproductHint" in {
      val conf = ConfigValueFactory.fromAnyRef("rainy-blue")
      ConfigConvert[Color].from(conf) shouldEqual Right(RainyBlue)
      val conf2 = ConfigValueFactory.fromAnyRef("sunny-yellow")
      ConfigConvert[Color].from(conf2) shouldEqual Right(SunnyYellow)
    }

    it should "write values as expected when using an EnumCoproductHint" in {
      val conf = ConfigConvert[Color].to(RainyBlue)
      conf shouldEqual ConfigValueFactory.fromAnyRef("rainy-blue")
      val conf2 = ConfigConvert[Color].to(SunnyYellow)
      conf2 shouldEqual ConfigValueFactory.fromAnyRef("sunny-yellow")
    }

    it should "fail to read values that are not case objects when using an EnumCoproductHint" in {
      val conf = ConfigFactory.parseString("{ which-animal = Dog, age = 2 }")
      ConfigConvert[AnimalConfig].from(conf.root()) should failWith(
        WrongType(ConfigValueType.OBJECT, Set(ConfigValueType.STRING)))
    }

    it should "fail to write values that are not case objects when using an EnumCoproductHint" in {
      val ex = the[ConfigReaderException[_]] thrownBy ConfigConvert[AnimalConfig].to(DogConfig(2))
      ex.failures.toList shouldEqual List(ConvertFailure(NonEmptyObjectFound("DogConfig"), None, ""))
    }
  }

  {
    implicit val hint = new FirstSuccessCoproductHint[AnimalConfig]

    it should "read values as expected when using a FirstSuccessCoproductHint" in {
      val conf = ConfigFactory.parseString("{ can-fly = true }")
      ConfigConvert[AnimalConfig].from(conf.root()) shouldBe Right(BirdConfig(true))
    }

    it should "write values as expected when using a FirstSuccessCoproductHint" in {
      val conf = ConfigConvert[AnimalConfig].to(DogConfig(2))
      conf shouldBe a[ConfigObject]
      conf.asInstanceOf[ConfigObject].get("which-animal") shouldBe null
    }
  }
}
