import com.google.inject.AbstractModule
import play.api.inject.ApplicationLifecycle
import play.api.{BuiltInComponents, Configuration, Environment}
import play.libs.Akka

trait A

class B extends A

class C extends A

class Module(environment: Environment, configuration: Configuration) extends AbstractModule{


  override def configure(): Unit = {
    val c: Option[String] =configuration.getString("c")
    if(c.isDefined)
    bind(classOf[A]).toInstance(new C)
    else
      bind(classOf[A]).toInstance(new B)

  }
}
