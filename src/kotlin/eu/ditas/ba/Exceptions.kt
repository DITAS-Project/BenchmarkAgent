package eu.ditas.ba

class FailedToDownload(msg: String?,thr:Throwable?): Exception(msg,thr){

}
class FailedTokenParsing(msg: String?, thr: Throwable?): Exception(msg,thr){}