* CMPL - MPS - Export
NAME          02_Standardmodell.cmpl
ROWS
 N  ziel    
 L  line[2] 
 L  line[3] 
COLUMNS
    x[2]      ziel                 2   line[2]       7.700000
    x[2]      line[3]       4.200000
    x[3]      ziel                 3   line[2]      10.500000
    x[3]      line[3]      11.100000
    GVANF     'MARKER'                 'INTORG'
    x[1]      ziel                 1   line[2]       5.600000
    x[1]      line[3]       9.800000
    GVEND     'MARKER'                 'INTEND'
RHS
    RHS       line[2]             15   line[3]             20
RANGES
BOUNDS
 PL BOUND     x[1]    
 PL BOUND     x[2]    
 PL BOUND     x[3]    
ENDATA