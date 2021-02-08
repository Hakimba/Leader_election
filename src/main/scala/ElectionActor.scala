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

     def getActorById(nodeId:Int) : ActorSelection = {
          return context.actorSelection("akka.tcp://LeaderSystem" + terminaux(nodeId).id + "@" + terminaux(nodeId).ip + ":" + terminaux(nodeId).port + "/user/Node/electionActor")
     }
     
     def getNeigh (nodes:List[Int]) : ActorSelection = {
          return getActorById(nodes((nodes.indexOf(this.id)+1)%(nodes.size))) // si on dépasse la capacité : 0
     }

     def receive = {
          // Initialisation
          case Start => {
               self ! Initiate
          }

          case StartWithNodeList (list) => {
               father ! Message ("Patientez...")
               Thread.sleep(2000) // si ça va trop vite, ça casse tout
               if (list.isEmpty) {
                    this.nodesAlive = this.nodesAlive:::List(id)
               }
               else {
                    this.nodesAlive = list
               }
               status match {
                    case Passive() =>
                    case _ => { // si c'est pas la première fois qu'on fait une election
                         status = Passive()
                    }
               }
               self ! Initiate
          }

          case Initiate => {
               status match {
                    case Passive() => {
                         status = new Candidate()
                         candSucc = -1
                         candPred = -1
                         if(nodesAlive.length == 1) {
                              status = new Waiting()
                              self ! AVSRSP(List(),id)
                         } else {
                              val r = getNeigh(nodesAlive)
                              r ! ALG (nodesAlive,this.id)
                         }
                         
                    }
                    case _ =>
               }
          }

          case ALG (list, init) => {
               status match {
                    case Passive() => {
                         status = new Dummy ()
                         val r = getNeigh(list)
                         r ! ALG (list,init)
                    }
                    case Candidate() => {
                         candPred = init
                         this.id match {
                              case a if a > init => {
                                   if(candSucc == -1) {
                                        status = new Waiting ()
                                        val r = getActorById(init)
                                        r ! AVS (list,this.id)
                                   } else {
                                        val r = getActorById(candSucc)
                                        r ! AVSRSP (list,candPred)
                                        status = new Dummy ()
                                   }
                              }
                              case b if b == init => {
                                   status = new Leader ()
                                   father ! LeaderChanged (this.id)
                              }
                              case _ =>
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
                              r ! AVSRSP (list,candPred)
                              status = new Dummy()
                         }
                    }
                    case Waiting () => {
                         candSucc = j
                    }
                    case _ =>
               }
          }

          case AVSRSP (list, k) => {
               status match {
                    case Waiting() => {
                         if (this.id == k) {
                              status = new Leader ()
                              father ! LeaderChanged (this.id)
                         } else {
                              candPred = k
                              if (candSucc == -1){
                                   if (k < this.id) {
                                        status = new Waiting ()
                                        val r = getActorById (k)
                                        r ! AVS (list,this.id)
                                   }
                              } else {
                                   status = new Dummy()
                                   val r = getActorById (candSucc)
                                   r ! AVSRSP (list,k)
                              }
                         }
                    }
                    case _ =>
               }
          }

     }
}