package sparsevec;

/**
 * `sparsevec` 的非法状态：下标越界、长度不一致、非法构造参数等。
 *
 * <p>本类已给全，**不要修改**。
 */
public class VecException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VecException(String message) {
        super(message);
    }
}
