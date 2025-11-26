import { motion, AnimatePresence } from 'framer-motion';

const CO2GraphModal = ({ isOpen, onClose, data }) => {
  if (!isOpen) return null;

  const maxVal = data.length > 0 ? Math.max(...data.map(d => d.cumulativeCo2Saved), 1) : 1;
  
  // Use 2:1 aspect ratio for internal coordinates to match container
  const width = 200;
  const height = 100;

  const points = data.map((d, i) => {
    const x = (i / (data.length - 1)) * width;
    const y = height - (d.cumulativeCo2Saved / maxVal) * height;
    return `${x},${y}`;
  }).join(' ');

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none"
          >
            <div className="bg-white dark:bg-gray-800 rounded-2xl p-6 w-full max-w-xl shadow-2xl pointer-events-auto mx-4 border border-gray-200 dark:border-gray-700">
              <div className="flex justify-between items-center mb-6">
                <h3 className="text-xl font-bold text-primary-900 dark:text-gray-100 font-display">
                  Cumulative CO₂ Savings ({new Date().getFullYear()})
                </h3>
                <button
                  onClick={onClose}
                  className="text-gray-500 hover:text-primary-600 dark:text-gray-400 dark:hover:text-primary-400 transition-colors"
                >
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>

              <div className="relative h-72 w-full mb-8">
                {data.length > 0 ? (
                  <svg className="w-full h-full overflow-visible" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
                    {/* Grid lines */}
                    {[0, 25, 50, 75, 100].map((y) => (
                      <line
                        key={y}
                        x1="0"
                        y1={y}
                        x2={width}
                        y2={y}
                        stroke="currentColor"
                        strokeOpacity="0.1"
                        strokeWidth="0.5"
                        vectorEffect="non-scaling-stroke"
                        className="text-gray-500 dark:text-gray-400"
                      />
                    ))}
                    
                    {/* Area under curve */}
                    <motion.path
                      initial={{ d: `M 0,${height} ${points.split(' ').map(p => p.split(',')[0] + ',' + height).join(' ')} ${width},${height}` }}
                      animate={{ d: `M 0,${height} ${points} ${width},${height}` }}
                      transition={{ duration: 1, ease: "easeOut" }}
                      fill="url(#gradient)"
                      className="opacity-20"
                    />
                    
                    {/* Line */}
                    <motion.polyline
                      initial={{ points: points.split(' ').map(p => p.split(',')[0] + ',' + height).join(' ') }}
                      animate={{ points }}
                      transition={{ duration: 1, ease: "easeOut" }}
                      fill="none"
                      stroke="url(#lineGradient)"
                      strokeWidth="3"
                      vectorEffect="non-scaling-stroke"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />

                    {/* Points */}
                    {data.map((d, i) => {
                      const x = (i / (data.length - 1)) * width;
                      const y = height - (d.cumulativeCo2Saved / maxVal) * height;
                      return (
                        <g key={i} className="group">
                          <motion.circle
                            initial={{ cy: height }}
                            animate={{ cy: y }}
                            transition={{ duration: 1, ease: "easeOut" }}
                            cx={x}
                            r="3"
                            className="fill-white dark:fill-gray-800 stroke-primary-600 dark:stroke-primary-400 stroke-2"
                            vectorEffect="non-scaling-stroke"
                          />
                          {/* Tooltip */}
                          <foreignObject x={x - 10} y={y - 20} width="20" height="20" className="overflow-visible">
                            <div className="opacity-0 group-hover:opacity-100 transition-opacity absolute bottom-full left-1/2 -translate-x-1/2 mb-2 bg-primary-900 dark:bg-gray-700 text-white text-xs font-medium py-1.5 px-3 rounded-lg shadow-lg whitespace-nowrap z-10 pointer-events-none">
                              {d.month}: {d.cumulativeCo2Saved.toFixed(2)} kg
                            </div>
                          </foreignObject>
                        </g>
                      );
                    })}

                    <defs>
                      <linearGradient id="gradient" x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stopColor="#64748b" />
                        <stop offset="100%" stopColor="#64748b" stopOpacity="0" />
                      </linearGradient>
                      <linearGradient id="lineGradient" x1="0" y1="0" x2="1" y2="0">
                        <stop offset="0%" stopColor="#475569" />
                        <stop offset="100%" stopColor="#64748b" />
                      </linearGradient>
                    </defs>
                  </svg>
                ) : (
                   <div className="w-full h-full flex items-center justify-center text-gray-500 dark:text-gray-400">
                     No data available
                   </div>
                )}
              </div>
              
              {/* X-Axis Labels */}
              <div className="flex justify-between text-xs font-medium text-gray-500 dark:text-gray-400 px-1">
                {data.map((d, i) => (
                  <div key={i} className="text-center" style={{ width: `${100 / data.length}%` }}>
                    {d.month}
                  </div>
                ))}
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};

export default CO2GraphModal;
