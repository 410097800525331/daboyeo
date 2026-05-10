package kr.daboyeo.backend.ingest;

public final class CgvBundleIngestCommand {

    private CgvBundleIngestCommand() {
    }

    public static void main(String[] args) throws Exception {
        String[] forwardedArgs = new String[args.length + 2];
        forwardedArgs[0] = "--provider";
        forwardedArgs[1] = "CGV";
        System.arraycopy(args, 0, forwardedArgs, 2, args.length);
        CollectorBundleIngestCommand.main(forwardedArgs);
    }
}
