package upmc.akka.leader

import akka.actor._

abstract class NodeStatus
case class Passive () extends NodeStatus
case class Candidate () extends NodeStatus
case class Dummy () extends NodeStatus
case class Waiting () extends NodeStatus
case class Leader () extends NodeStatus

abstract class LeaderAlgoMessage
case class Initiate () extends LeaderAlgoMessage
case class ALG (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVS (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVSRSP (list:List[Int], nodeId:Int) extends LeaderAlgoMessage

case class StartWithNodeList (list:List[Int])

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {

     val father = context.parent
     var nodesAlive:List[Int] = List(id)

     var candSucc:Int = -1
     var candPred:Int = -1
     var status:NodeStatus = new Passive ()

// assez sale pour l'instant niveau gestion d'erreurs
     def getActorById (id:Int):ActorSelection = {
          var remote:ActorSelection =  null
          if (id < nodesAlive.length){
               terminaux.foreach(n => {
                    if (n.id == id) {
                         remote = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
                    }
               })
          } else {
               println("Id > length of the list of Actors")
          }
          
          return remote
     }

     def getNeigh (id:Int) : ActorSelection = {
          var remote:ActorSelection = null
          if (id >= nodesAlive.length){
               remote = getActorById(0)
          } else {
               remote = getActorById(id)
          }
          return remote
     }

     def receive = {
          // Initialisation
          case Start => {
               self ! Initiate
          }

          case StartWithNodeList (list) => {
               if (list.isEmpty) {
                    this.nodesAlive = this.nodesAlive:::List(id)
               }
               else {
                    this.nodesAlive = list
               }

               // Debut de l'algorithme d'election
               self ! Initiate
          }

          case Initiate => {
               val r = getNeigh(this.id)
               r ! ALG (nodesAlive,this.id)
          }
// je passe des listes en arguments mais je les utilise pas
// je sais pas a quoi elles servent
// c'est la specification du prof

          case ALG (list, init) => {
               status match {
                    case Passive() => {
                         status = new Dummy ()
                         val r = getNeigh(this.id)
                         r ! ALG (nodesAlive,init)
                    }
                    case Candidate() => {
                         candPred = init
                         this.id match {
                              case a if a > init => {
                                   if(candSucc == -1) {
                                        status = new Waiting ()
                                        val r = getActorById(init)
                                        r ! AVS (nodesAlive,this.id)
                                   } else {
                                        val r = getActorById(candSucc)
                                        r ! AVSRSP (nodesAlive,candPred)
                                        status = new Dummy ()
                                   }
                              }
                              case b if b == init => {
                                   status = new Leader ()
                              }
                         }
                    }
                    case _ =>
               }
          }

          case AVS (list, j) => {
               status match {
                    case Candidate () => {
                         if (candPred == -1) {
                              candSucc = j
                         } else {
                              val r = getActorById (j)
                              r ! AVSRSP (nodesAlive,candPred)
                              status = new Dummy()
                         }
                    }
                    case Waiting () => {
                         candSucc = j
                    }
               }
          }

          case AVSRSP (list, k) => {
               status match {
                    case Waiting() => {
                         if (this.id == k) {
                              status = new Leader()
                         } else {
                              candPred = k
                              if (candSucc == -1){
                                   if (k < this.id) {
                                        status = new Waiting ()
                                        val r = getActorById (k)
                                        r ! AVS (nodesAlive,this.id)
                                   }
                              } else {
                                   status = new Dummy()
                                   val r = getActorById (candSucc)
                                   r ! AVSRSP (nodesAlive,k)
                              }
                         }
                    }
               }
          }

     }
}