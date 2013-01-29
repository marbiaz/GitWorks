package gitworks;


interface DfsOperator {

int getID();

boolean runOnce();

void initialize(ForkEntry f);

void run(ForkEntry fe, Object res) throws Exception;

void finalize(ForkEntry f);

}
